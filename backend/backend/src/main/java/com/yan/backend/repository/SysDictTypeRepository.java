package com.yan.backend.repository;

import com.yan.backend.entity.SysDictType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SysDictTypeRepository extends JpaRepository<SysDictType, Long> {

    Optional<SysDictType> findByDictType(String dictType);

    boolean existsByDictType(String dictType);

    List<SysDictType> findAllByOrderByIdAsc();
}
